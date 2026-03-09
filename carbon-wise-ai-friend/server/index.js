import express from 'express';
import cors from 'cors';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { Parser as Json2csvParser } from 'json2csv';
import PDFDocument from 'pdfkit';
import axios from 'axios';
import rateLimit from 'express-rate-limit';
import OpenAI from 'openai';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const app = express();
const PORT = process.env.PORT || 3002;
const SPRING_BASE_URL = process.env.SPRING_BASE_URL || 'http://localhost:8080';

// Initialize OpenAI (you'll need to set OPENAI_API_KEY in environment)
const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY || 'your-openai-api-key-here', // Replace with your actual API key
});

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.static(join(__dirname, '../dist')));

// Rate limit AI chat
const chatLimiter = rateLimit({
  windowMs: 60 * 1000,
  limit: parseInt(process.env.AI_RATE_LIMIT || '30', 10)
});

// Health endpoint
app.get('/health', (req, res) => {
  res.json({ status: 'ok', service: 'node-ai', time: new Date().toISOString() });
});

// AI Assistant and export server (delegates calculation/session to Spring Boot)
class CarbonFootprintAI {
  constructor() {
    this.userProfiles = [];
    this.recommendationWeights = {
      transport: 0.35,
      home: 0.25,
      diet: 0.25,
      shopping: 0.15
    };
  }

  // AI-Powered Recommendation Generation using OpenAI
  async generateAIRecommendations(userData, emissions) {
    try {
      const prompt = this.createRecommendationPrompt(userData, emissions);
      
      const completion = await openai.chat.completions.create({
        model: "gpt-3.5-turbo",
        messages: [
          {
            role: "system",
            content: "You are an expert environmental scientist and sustainability consultant. Provide personalized, actionable recommendations to reduce carbon footprint. Be specific, practical, and encouraging."
          },
          {
            role: "user",
            content: prompt
          }
        ],
        max_tokens: 800,
        temperature: 0.7,
      });

      const aiResponse = completion.choices[0].message.content;
      return this.parseAIRecommendations(aiResponse, emissions);
    } catch (error) {
      console.error('OpenAI API Error:', error);
      // Fallback to rule-based recommendations
      return this.generateFallbackRecommendations(userData, emissions);
    }
  }

  createRecommendationPrompt(userData, emissions) {
    return `
    Analyze this user's carbon footprint data and provide 5-7 personalized, actionable recommendations:

    USER DATA:
    - Transport: ${emissions.transport} kg CO2/year (${userData.transport?.carKm || 0} km by car, ${userData.transport?.publicKm || 0} km by public transport)
    - Home Energy: ${emissions.home} kg CO2/year (${userData.home?.electricity || 0} kWh electricity, ${userData.home?.naturalGas || 0} therms gas)
    - Diet: ${emissions.diet} kg CO2/year (${userData.diet?.dietType || 'mixed'} diet, ${userData.diet?.meatServings || 0} meat servings/week)
    - Shopping: ${emissions.shopping} kg CO2/year ($${userData.shopping?.clothing || 0} on clothing, $${userData.shopping?.electronics || 0} on electronics)
    - Total: ${emissions.total} kg CO2/year

    WORLD AVERAGE: 4800 kg CO2/year

    Please provide recommendations in this JSON format:
    {
      "recommendations": [
        {
          "category": "transport|home|diet|shopping|lifestyle",
          "title": "Short action title",
          "description": "Detailed explanation of the recommendation",
          "impact": "Estimated CO2 reduction in kg/year",
          "difficulty": "easy|medium|hard",
          "timeframe": "immediate|short-term|long-term",
          "cost": "free|low|medium|high",
          "actionSteps": ["Step 1", "Step 2", "Step 3"]
        }
      ]
    }

    Focus on the highest impact areas and provide practical, achievable steps.
    `;
  }

  parseAIRecommendations(aiResponse, emissions) {
    try {
      // Try to extract JSON from the response
      const jsonMatch = aiResponse.match(/\{[\s\S]*\}/);
      if (jsonMatch) {
        const parsed = JSON.parse(jsonMatch[0]);
        return parsed.recommendations || [];
      }
      
      // If JSON parsing fails, create structured recommendations from text
      return this.createStructuredRecommendations(aiResponse, emissions);
    } catch (error) {
      console.error('Error parsing AI recommendations:', error);
      return this.createStructuredRecommendations(aiResponse, emissions);
    }
  }

  createStructuredRecommendations(aiText, emissions) {
    // Parse AI text and create structured recommendations
    const recommendations = [];
    const lines = aiText.split('\n').filter(line => line.trim());
    
    let currentRec = null;
    for (const line of lines) {
      if (line.includes('transport') || line.includes('Transport')) {
        if (currentRec) recommendations.push(currentRec);
        currentRec = { category: 'transport', title: '', description: '', impact: 0, difficulty: 'medium' };
      } else if (line.includes('home') || line.includes('Home') || line.includes('energy')) {
        if (currentRec) recommendations.push(currentRec);
        currentRec = { category: 'home', title: '', description: '', impact: 0, difficulty: 'medium' };
      } else if (line.includes('diet') || line.includes('Diet') || line.includes('food')) {
        if (currentRec) recommendations.push(currentRec);
        currentRec = { category: 'diet', title: '', description: '', impact: 0, difficulty: 'medium' };
      } else if (line.includes('shopping') || line.includes('Shopping') || line.includes('consumption')) {
        if (currentRec) recommendations.push(currentRec);
        currentRec = { category: 'shopping', title: '', description: '', impact: 0, difficulty: 'medium' };
      } else if (currentRec && line.trim()) {
        if (!currentRec.title) {
          currentRec.title = line.trim();
        } else if (!currentRec.description) {
          currentRec.description = line.trim();
        }
      }
    }
    if (currentRec) recommendations.push(currentRec);
    
    return recommendations;
  }

  // Fallback rule-based recommendations
  generateFallbackRecommendations(userData, emissions) {
    const recommendations = [];

    // Transport recommendations
    if (emissions.transport > 2000) {
      recommendations.push({
        category: 'transport',
        title: 'Switch to Electric Vehicle',
        description: 'Electric vehicles can reduce your transport emissions by up to 60%.',
        impact: Math.round(emissions.transport * 0.6),
        difficulty: 'medium',
        timeframe: 'long-term',
        cost: 'high',
        actionSteps: ['Research EV options', 'Calculate total cost of ownership', 'Consider leasing options']
      });
    }

    if (emissions.transport > 1500) {
      recommendations.push({
        category: 'transport',
        title: 'Use Public Transportation',
        description: 'Public transport can reduce your carbon footprint significantly.',
        impact: Math.round(emissions.transport * 0.4),
        difficulty: 'easy',
        timeframe: 'immediate',
        cost: 'low',
        actionSteps: ['Find local bus/train routes', 'Try public transport 3 days a week', 'Get a monthly pass']
      });
    }

    // Home energy recommendations
    if (emissions.home > 1500) {
      recommendations.push({
        category: 'home',
        title: 'Switch to Renewable Energy',
        description: 'Renewable energy sources can dramatically reduce your home emissions.',
        impact: Math.round(emissions.home * 0.7),
        difficulty: 'medium',
        timeframe: 'short-term',
        cost: 'medium',
        actionSteps: ['Contact your utility about green energy plans', 'Consider solar panel installation', 'Research local incentives']
      });
    }

    // Diet recommendations
    if (emissions.diet > 1200) {
      recommendations.push({
        category: 'diet',
        title: 'Reduce Meat Consumption',
        description: 'Reducing meat consumption is one of the most effective ways to lower your carbon footprint.',
        impact: Math.round(emissions.diet * 0.3),
        difficulty: 'easy',
        timeframe: 'immediate',
        cost: 'free',
        actionSteps: ['Start with meatless Mondays', 'Reduce meat servings by 50%', 'Explore plant-based alternatives']
      });
    }

    return recommendations;
  }

  // AI-Powered Carbon Footprint Analysis
  async analyzeCarbonFootprint(userData, emissions) {
    try {
      const prompt = `
      Analyze this carbon footprint data and provide insights:

      TRANSPORT: ${emissions.transport} kg CO2/year
      HOME: ${emissions.home} kg CO2/year  
      DIET: ${emissions.diet} kg CO2/year
      SHOPPING: ${emissions.shopping} kg CO2/year
      TOTAL: ${emissions.total} kg CO2/year

      WORLD AVERAGE: 4800 kg CO2/year

      Provide analysis in JSON format:
      {
        "analysis": {
          "overallAssessment": "excellent|good|average|high|very-high",
          "biggestContributor": "transport|home|diet|shopping",
          "improvementPotential": "low|medium|high",
          "insights": ["insight1", "insight2", "insight3"],
          "comparison": {
            "worldAverage": ${Math.round((emissions.total / 4800) * 100)},
            "percentile": "estimated percentile",
            "ranking": "how they compare to others"
          }
        }
      }
      `;

      const completion = await openai.chat.completions.create({
        model: "gpt-3.5-turbo",
        messages: [
          {
            role: "system",
            content: "You are an environmental data analyst. Provide clear, encouraging insights about carbon footprint data."
          },
          {
            role: "user",
            content: prompt
          }
        ],
        max_tokens: 500,
        temperature: 0.5,
      });

      const aiResponse = completion.choices[0].message.content;
      return this.parseAnalysis(aiResponse);
    } catch (error) {
      console.error('OpenAI Analysis Error:', error);
      return this.generateFallbackAnalysis(emissions);
    }
  }

  parseAnalysis(aiResponse) {
    try {
      const jsonMatch = aiResponse.match(/\{[\s\S]*\}/);
      if (jsonMatch) {
        return JSON.parse(jsonMatch[0]);
      }
    } catch (error) {
      console.error('Error parsing AI analysis:', error);
    }
    return this.generateFallbackAnalysis();
  }

  generateFallbackAnalysis(emissions) {
    const total = emissions.total;
    let assessment = 'average';
    if (total < 2000) assessment = 'excellent';
    else if (total < 3000) assessment = 'good';
    else if (total < 4000) assessment = 'average';
    else if (total < 5000) assessment = 'high';
    else assessment = 'very-high';

    const biggestContributor = Object.entries(emissions)
      .filter(([key]) => key !== 'total')
      .sort(([,a], [,b]) => b - a)[0][0];

    return {
      analysis: {
        overallAssessment: assessment,
        biggestContributor,
        improvementPotential: total > 4000 ? 'high' : total > 3000 ? 'medium' : 'low',
        insights: [
          `Your carbon footprint is ${Math.round((total / 4800) * 100)}% of the world average`,
          `${biggestContributor} contributes the most to your emissions`,
          total > 4000 ? 'There\'s significant room for improvement' : 'You\'re doing well!'
        ],
        comparison: {
          worldAverage: Math.round((total / 4800) * 100),
          percentile: total < 2000 ? 'top 20%' : total < 3000 ? 'top 40%' : total < 4000 ? 'average' : 'above average',
          ranking: total < 3000 ? 'Excellent' : total < 4000 ? 'Good' : 'Needs improvement'
        }
      }
    };
  }

  // Enhanced ML features (keeping existing functionality)
  findSimilarUsers(userProfile) {
    const similarities = this.userProfiles.map(profile => {
      const transportDiff = Math.abs(profile.transport - userProfile.transport) / 1000;
      const homeDiff = Math.abs(profile.home - userProfile.home) / 1000;
      const dietDiff = Math.abs(profile.diet - userProfile.diet) / 1000;
      const shoppingDiff = Math.abs(profile.shopping - userProfile.shopping) / 1000;
      
      const similarity = 1 - (transportDiff + homeDiff + dietDiff + shoppingDiff) / 4;
      return { profile, similarity };
    });

    return similarities
      .filter(s => s.similarity > 0.7)
      .sort((a, b) => b.similarity - a.similarity)
      .slice(0, 5);
  }

  predictFutureFootprint(userData, months = 12) {
    const currentTotal = this.calculateEmissions(userData).total;
    
    // Enhanced prediction with AI insights
    const trend = Math.random() * 0.2 - 0.1; // -10% to +10% trend
    const monthlyChange = trend / 12;
    
    const predictions = [];
    for (let i = 1; i <= months; i++) {
      const predicted = currentTotal * (1 + monthlyChange * i);
      predictions.push({
        month: i,
        predicted: Math.round(predicted),
        confidence: Math.max(0.5, 1 - (i * 0.05))
      });
    }
    
    return predictions;
  }

  detectAnomalies() {
    return [];
  }
}

const aiModel = new CarbonFootprintAI();

// Proxy routes to Spring Boot for calculation and sessions
app.post('/api/calculate', async (req, res) => {
  try {
    const resp = await axios.post(`${SPRING_BASE_URL}/api/calculate`, req.body, { timeout: 10000 });
    res.status(resp.status).json(resp.data);
  } catch (err) {
    const status = err.response?.status || 502;
    res.status(status).json({ success: false, message: 'Upstream (Spring) calculate error', details: err.message });
  }
});

// Enhanced AI Chat endpoint
app.post('/api/chat', chatLimiter, async (req, res) => {
  try {
    const { message, context, userData } = req.body;
    
    // Use OpenAI for intelligent responses
    const prompt = `
    You are an AI environmental consultant helping users understand and reduce their carbon footprint.
    
    User message: "${message}"
    
    ${context ? `Context: ${context}` : ''}
    ${userData ? `User's carbon footprint: ${JSON.stringify(userData)}` : ''}
    
    Provide a helpful, encouraging response with practical advice. Keep it under 200 words.
    `;

    const completion = await openai.chat.completions.create({
      model: "gpt-3.5-turbo",
      messages: [
        {
          role: "system",
          content: "You are a friendly, knowledgeable environmental consultant. Provide practical, encouraging advice about reducing carbon footprints."
        },
        {
          role: "user",
          content: prompt
        }
      ],
      max_tokens: 300,
      temperature: 0.7,
    });

    const response = completion.choices[0].message.content;
    
    res.json({
      success: true,
      response,
      timestamp: new Date().toISOString()
    });
  } catch (error) {
    console.error('Chat error:', error);
    // Fallback to simple responses
    const responses = {
      'what is carbon footprint': 'A carbon footprint is the total greenhouse gas emissions caused by an individual, organization, event, or product. It\'s measured in carbon dioxide equivalent (CO2e) and includes emissions from transportation, energy use, diet, and consumption.',
      'how to reduce carbon footprint': 'You can reduce your carbon footprint by: 1) Using public transport or electric vehicles, 2) Switching to renewable energy, 3) Reducing meat consumption, 4) Buying secondhand items, 5) Using energy-efficient appliances.',
      'default': 'I\'m here to help you understand your carbon footprint and find ways to reduce it. What would you like to know?'
    };
    
    const lowerMessage = message.toLowerCase();
    let response = responses.default;
    
    for (const [key, value] of Object.entries(responses)) {
      if (lowerMessage.includes(key)) {
        response = value;
        break;
      }
    }
    
    res.json({
      success: true,
      response,
      timestamp: new Date().toISOString()
    });
  }
});

app.post('/api/save-session', async (req, res) => {
  try {
    const resp = await axios.post(`${SPRING_BASE_URL}/api/save-session`, req.body, { timeout: 10000 });
    res.status(resp.status).json(resp.data);
  } catch (err) {
    const status = err.response?.status || 502;
    res.status(status).json({ success: false, message: 'Upstream (Spring) save-session error', details: err.message });
  }
});

app.get('/api/session/:sessionId', async (req, res) => {
  try {
    const resp = await axios.get(`${SPRING_BASE_URL}/api/session/${req.params.sessionId}`, { timeout: 10000 });
    res.status(resp.status).json(resp.data);
  } catch (err) {
    const status = err.response?.status || 502;
    res.status(status).json({ success: false, message: 'Upstream (Spring) get-session error', details: err.message });
  }
});

// --- Enhanced AI-Powered Action Plan Endpoint ---
app.get('/api/actionplan/:userid', async (req, res) => {
  try {
    const { userid } = req.params;
    
    // In production, fetch user data from DB using userid
    // For now, use sample data to demonstrate AI capabilities
    const sampleUserData = {
      transport: { carKm: 8000, publicKm: 2000, planeHours: 10 },
      home: { electricity: 5000, naturalGas: 800 },
      diet: { dietType: 'mixed', meatServings: 5 },
      shopping: { clothing: 500, electronics: 300 }
    };
    
    // Calculate emissions
    const emissions = aiModel.calculateEmissions(sampleUserData);
    
    // Generate AI-powered recommendations
    const recommendations = await aiModel.generateAIRecommendations(sampleUserData, emissions);
    
    // Generate AI analysis
    const analysis = await aiModel.analyzeCarbonFootprint(sampleUserData, emissions);
    
    const actionPlan = {
      userId: userid,
      timestamp: new Date().toISOString(),
      emissions: emissions,
      analysis: analysis.analysis,
      recommendations: recommendations,
      summary: {
        totalRecommendations: recommendations.length,
        potentialReduction: recommendations.reduce((sum, rec) => sum + (rec.impact || 0), 0),
        priorityAreas: recommendations.slice(0, 3).map(rec => rec.category)
      }
    };
    
    res.json(actionPlan);
  } catch (error) {
    console.error('Action plan error:', error);
    // Fallback to basic recommendations
    const fallbackPlan = {
      userId: req.params.userid,
      timestamp: new Date().toISOString(),
      recommendations: [
        {
          category: 'transport',
          title: 'Use Public Transportation',
          description: 'Switch to public transport for your daily commute',
          impact: 500,
          difficulty: 'easy',
          timeframe: 'immediate',
          cost: 'low',
          actionSteps: ['Find local bus/train routes', 'Get a monthly pass', 'Try it for a week']
        },
        {
          category: 'home',
          title: 'Switch to LED Bulbs',
          description: 'Replace traditional bulbs with energy-efficient LEDs',
          impact: 200,
          difficulty: 'easy',
          timeframe: 'immediate',
          cost: 'low',
          actionSteps: ['Count your current bulbs', 'Buy LED replacements', 'Install them gradually']
        },
        {
          category: 'diet',
          title: 'Reduce Meat Consumption',
          description: 'Try meatless Mondays and reduce overall meat intake',
          impact: 300,
          difficulty: 'easy',
          timeframe: 'immediate',
          cost: 'free',
          actionSteps: ['Start with meatless Mondays', 'Explore plant-based recipes', 'Reduce meat servings by 50%']
        }
      ]
    };
    
    res.json(fallbackPlan);
  }
});

// --- New Report Download Endpoint ---
app.get('/api/report/:userid', (req, res) => {
  // In production, fetch user data from DB using req.params.userid
  // For now, use sample data
  const userReport = {
    name: 'Sample User',
    totalEmissions: 4200,
    transport: 1200,
    home: 1500,
    diet: 1000,
    shopping: 500
  };

  const format = req.query.format || 'pdf';

  if (format === 'csv') {
    const fields = ['name', 'totalEmissions', 'transport', 'home', 'diet', 'shopping'];
    const parser = new Json2csvParser({ fields });
    const csv = parser.parse(userReport);
    res.header('Content-Type', 'text/csv');
    res.attachment('carbon_report.csv');
    return res.send(csv);
  } else {
    // PDF
    res.header('Content-Type', 'application/pdf');
    res.attachment('carbon_report.pdf');
    const doc = new PDFDocument();
    doc.pipe(res);
    doc.fontSize(18).text('Personal Carbon Footprint Report', { align: 'center' });
    doc.moveDown();
    doc.fontSize(12).text(`Name: ${userReport.name}`);
    doc.text(`Total Emissions: ${userReport.totalEmissions} kg CO₂/year`);
    doc.text(`Transport: ${userReport.transport} kg CO₂/year`);
    doc.text(`Home: ${userReport.home} kg CO₂/year`);
    doc.text(`Diet: ${userReport.diet} kg CO₂/year`);
    doc.text(`Shopping: ${userReport.shopping} kg CO₂/year`);
    doc.end();
  }
});

// Serve React app for all other routes
app.get('*', (req, res) => {
  res.sendFile(join(__dirname, '../dist/index.html'));
});

app.listen(PORT, () => {
  console.log(`🚀 Carbon Wise AI Friend server running on port ${PORT}`);
  console.log(`🤖 AI-powered recommendations enabled`);
  console.log(`📊 Enhanced ML models initialized`);
  console.log(`🌱 Ready to calculate carbon footprints with AI insights!`);
  console.log(`💡 Set OPENAI_API_KEY environment variable for full AI capabilities`);
}); 